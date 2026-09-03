# TB-Helfer – High-Impact Patch (KSP2 + Release-Optimierung)

## Ziel
1) **KSP1-Warnung loswerden** ("We noticed you are using KSP1…") durch Umstellung auf **KSP2**.
2) **Release kleiner/schneller** machen (R8 + resource shrinking) – Debug bleibt unverändert.
3) Nebenbei: ein paar "klassische" Build-Painpoints entschärfen (META-INF Doppel-Einträge, Room-Schema-Export).

---

## Was geändert wurde

### 1) `gradle.properties` (NEU / Merge)
**Neu hinzugefügt:**
- `ksp.useKSP2=true`  → schaltet KSP2 ein und entfernt die KSP1-Deprecation-Warnung. 
- Performance-Flags: `org.gradle.parallel=true`, `org.gradle.caching=true`, `android.nonTransitiveRClass=true`, `android.nonFinalResIds=true`.

**Warum:** KSP2 wird über genau dieses Gradle-Flag aktiviert. citeturn0search0turn0search9

> ⚠️ Falls du bereits eine `gradle.properties` hast: **nicht ersetzen**, sondern **merge**.

### 2) `app/build.gradle.kts` (UPDATE)
**Release-Optimierung (aggressiv aber üblich):**
- `release { isMinifyEnabled = true; isShrinkResources = true }`

**Room KSP – Schema Export:**
- `ksp { arg("room.schemaLocation", "$projectDir/schemas") ... }`

**Packaging-Fix:**
- `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`

### 3) `app/schemas/` (NEU)
- Ordner für Room-Schemas (mit `.gitkeep`), damit der Export-Pfad garantiert existiert.

---

## Wie du es anwendest (lokal in deinem Projekt)

1) **`gradle.properties`**
   - Wenn vorhanden: die Zeilen aus `./gradle.properties` **in deine bestehende** root-`gradle.properties` kopieren.
   - Wenn nicht vorhanden: Datei im **Projekt-Root** anlegen.

2) **`app/build.gradle.kts`**
   - Datei 1:1 ersetzen durch die Version aus diesem Patch.

3) **Build testen**
```bash
./gradlew clean :app:assembleDebug :app:assembleRelease
```

---

## Erwartete Effekte
- **KSP1-Warnung weg**, weil KSP2 aktiv ist. citeturn0search0turn0search2
- **Release-APK/AAB kleiner**, weil R8 + resource shrinking aktiv sind.
- **Room-Schema-Dateien** landen unter `app/schemas/` (hilfreich für DB-Migrationen & spätere Tests).

---

## Falls etwas schiefgeht (Rollback / Troubleshooting)

### A) Release crasht / irgendwas fehlt nur im Release
- Quick-Fix: `isMinifyEnabled = false` und/oder `isShrinkResources = false` im Release-Block.
- Danach gezielt ProGuard/R8 Rules nachziehen.

### B) KSP2 verursacht Processor-Probleme
- Sehr selten bei Room, aber möglich bei manchen Annotation-Prozessoren.
- Quick-Fix: `ksp.useKSP2=false` (nur zum Eingrenzen).
- Dann gezielt Processor/Plugin-Versionen checken.

---

## Dateien in diesem Patch
- `gradle.properties`
- `app/build.gradle.kts`
- `app/proguard-rules.pro` (unverändert, nur mit Release jetzt aktiv)
- `app/schemas/.gitkeep`
