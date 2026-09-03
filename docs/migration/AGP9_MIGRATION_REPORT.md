# AGP 9 Migration – TB_Helfer (High-Impact)

## Kurzfazit
Du bist in **zwei harte AGP-9/AndroidX-Hürden** gelaufen:

1) **compileSdk zu niedrig** für deine AndroidX-Versionen (z.B. `androidx.core:core-ktx:1.17.0` verlangt **compileSdk 36**) → `:app:checkDebugAarMetadata`  
2) **minSdk zu niedrig** für CameraX 1.5.x (insb. `camera-video`) verlangt **minSdk 23** → Manifest-Merger-Fehler

Ich habe die Migration so umgesetzt, dass du **mit AGP 9 sofort wieder bauen kannst**, ohne dein komplettes Kotlin/KSP-Setup zu zerlegen.

**AGP 9 Compatibility-Basis (laut Release Notes):** Gradle 9.1.0, Build Tools 36.0.0, JDK 17, max API 36.  
Quelle: https://developer.android.com/build/releases/agp-9-0-0-release-notes

---

## Was ich geändert habe

### 1) AGP / Gradle Wrapper
- **AGP**: `com.android.application` von **8.13.2** → **9.0.0**
- **Gradle Wrapper**: von **8.13** → **9.1.0**

Dateien:
- `build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`

---

### 2) built-in Kotlin (AGP 9 Default) – bewusst opt-out
AGP 9 aktiviert **built-in Kotlin** standardmäßig. Wenn man weiterhin `org.jetbrains.kotlin.android` anwendet, knallt es oft direkt.

Da du **Room + KSP** nutzt, ist die robuste Route für „buildbar sofort“:
- externes Kotlin-Plugin bleibt aktiv
- built-in Kotlin + new DSL werden per Properties deaktiviert

Änderungen in `gradle.properties`:
- `android.builtInKotlin=false`
- `android.newDsl=false`

Quelle (Migration Doc): https://developer.android.com/build/migrate-to-built-in-kotlin

Zusatz: **KSP1** ist für **AGP 9+** und **Kotlin 2.3+** nicht mehr kompatibel → `ksp.useKSP2=true` ist korrekt.
Quelle (KSP Repo, Compatibility Limitations): https://github.com/google/ksp

---

### 3) compileSdk / targetSdk / minSdk
**Fix für `checkDebugAarMetadata`:**
- `compileSdk = 36` (vorher 35)
- `targetSdk = 36` (vorher 35) – optional, hier bewusst „alles aktuell“

**Fix für Manifest-Merger mit CameraX:**
- `minSdk = 23` (vorher 21)

Datei:
- `app/build.gradle.kts`

---

### 4) Library-Updates (klein, aber sinnvoll)
- CameraX: `1.5.2` → `1.5.3` (stabiler Patchlevel)
  Quelle: https://developer.android.com/jetpack/androidx/releases/camera
- KSP Plugin: `2.3.4` → `2.3.5` (aktueller Release)
  Quellen: https://github.com/google/ksp/releases | https://mvnrepository.com/artifact/com.google.devtools.ksp/com.google.devtools.ksp.gradle.plugin/versions

---

## Was du in Android Studio machen musst
Damit `compileSdk 36` sauber baut, brauchst du lokal:
1) **Android SDK Platform API 36** installieren
2) **Build Tools 36.0.0** installieren

In Android Studio:
- *Settings/Preferences → Android SDK → SDK Platforms* → **Android 16 / API 36** anhaken
- *SDK Tools* → **Android SDK Build-Tools 36.0.0** anhaken
- Sync → Rebuild

---

## Wenn du minSdk 21 unbedingt behalten willst (Alternativen)
Du hast drei Optionen – von „sauber“ bis „risikoreich“:

### Option A (sauber): CameraX downgraden
Nimm eine ältere CameraX-Version, die noch minSdk 21 akzeptiert. Dann kannst du `minSdk=21` lassen.
- Nachteil: du bleibst hinter aktuellen Fixes zurück.

### Option B (evtl. möglich): `camera-video` raus-excluden
Wenn du Video gar nicht nutzt, kann man versuchen, `androidx.camera:camera-video` auszuschließen.
- Risiko: wenn CameraX intern inzwischen drauf baut, knallt es beim Build/Runtime.

Beispiel:
```kotlin
implementation("androidx.camera:camera-lifecycle:$cameraxVersion") {
    exclude(group = "androidx.camera", module = "camera-video")
}
```

### Option C (nicht empfohlen): `tools:overrideLibrary` erzwingen
Das umgeht nur den Merger und kann auf API 21/22 zur Runtime crashen.

---

## Wenn du *voll* auf built-in Kotlin migrieren willst (später)
Dann gilt grob:
- `android.builtInKotlin=false` + `android.newDsl=false` entfernen
- `org.jetbrains.kotlin.android` Plugin entfernen
- KSP/Room erneut prüfen (weil KSP & built-in Kotlin aktuell oft nicht „out of the box“ harmonieren)

Offizielle Anleitung: https://developer.android.com/build/migrate-to-built-in-kotlin

---

## Deliverables
- **Projekt-ZIP (AGP 9 + Fixes):** `APP_AGP9_migrated.zip`
- **Patch (diff zur High-Impact-Allin Basis):** `AGP9_MIGRATION.patch`

Geänderte Dateien:
- `build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle.properties`
- `app/build.gradle.kts`
