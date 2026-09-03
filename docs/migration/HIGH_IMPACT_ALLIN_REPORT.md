# High-Impact „mach alles“ – Cleanup & Optimierung

Zeitpunkt: 2026-01-30 11:25:10.133775

## Ziel
- Dublikate entfernen
- Nicht-benutzte/unnötige Daten raus
- Querprüfen (Build-Setup, AndroidX/KSP, Ressourcen, Manifest)
- Optimierungen, die **direkt** spürbar sind (Build- & Runtime-Qualität), ohne die App-Logik umzuschreiben

---

## 1) Gelöscht (Build-/IDE-Müll & Alt-Artefakte)

**Warum:** Diese Dateien/Ordner gehören nicht ins Repo/ZIP (werden bei jedem Build/Sync neu erzeugt) und machen das Projekt unnötig groß/träge.

Gelöscht (16):
- `.gradle`
- `.idea`
- `.kotlin`
- `build`
- `local.properties`
- `app/build`
- `HIGH_IMPACT.patch`
- `HIGH_IMPACT_REPORT.md`
- `app/AGGRESSIVE_CLEANUP_REPORT.md`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp`
- `app/src/main/APP2_cleaned.zip`

> Hinweis: `local.properties` wird absichtlich entfernt (enthält lokale Pfade und gehört nicht in Git/ZIP).

---

## 2) Dedupe / Zusammenlegen

### Launcher-Icon „Round“ zusammengelegt
- `@mipmap/ic_launcher_round` wurde auf `@mipmap/ic_launcher` umgestellt (Manifest).
- Die doppelten Round-Icon-Dateien wurden entfernt (waren identisch / nicht mehr referenziert).

Ergebnis: weniger Ressourcen-Dateien, gleiche Funktion.

---

## 3) Geändert (Build & Manifest)

### AndroidManifest.xml
- `uses-feature` für Kamera ergänzt:
  - `android.hardware.camera` / `required="false"`

**Warum:** Play Store/Devices wissen so korrekt, dass die App *Kamera nutzen kann*, aber *nicht zwingend braucht*.

### app/build.gradle.kts
- `compileSdk = 35` (stabil)
- `targetSdk = 35` (Play-Anforderung)
- `minSdk = 21` (realistische Geräte-Abdeckung)
- **Core Library Desugaring aktiviert**, damit `java.time.*` auf älteren Android-Versionen läuft:
  - `isCoreLibraryDesugaringEnabled = true`
  - `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")`

**Warum:** Dein Code nutzt `java.time` in mehreren Dateien. Ohne Desugaring müsstest du minSdk unnötig hochziehen.

### build.gradle.kts (Root)
- Kotlin: `2.3.0`
- KSP: `2.3.4`
- Kommentar zur KSP-Version aktualisiert.

### gradle.properties
- Properties gecheckt/abgesichert (AndroidX + KSP2 + Build-Performance Flags). (Falls schon vorhanden, wird nichts doppelt gesetzt.)

### .gitignore (neu)
- Standard-Regeln für Gradle/AS/Build-Ausgaben ergänzt.

---

## 4) Querprüfung (was zusammenhängt)

- **AndroidX Build-Fehler** (CheckAarMetadata) → wird über `android.useAndroidX=true` + `android.enableJetifier=true` verhindert.
- **KSP1 Deprecation** → `ksp.useKSP2=true` bleibt aktiv.
- **java.time Nutzung** → minSdk runter + **Desugaring** an (sonst Crashes/Compile-Probleme auf älteren Geräten).
- **Kamera Permission** + Widgets → `uses-feature camera required=false` macht Manifest sauberer & verhindert unnötige Device-Excludes.

---

## 5) Vorschläge (Optional, aber „High Impact“)

### A) AGP 9.0 Upgrade (nur wenn du willst)
AGP 9 bringt Breaking Changes (Built-in Kotlin / neue DSL). Das ist **ein eigener Schritt** und sollte bewusst gemacht werden.

### B) Ressourcen/Strings „härten“
Wenn du viele Hardcoded-Strings im Layout hast:
- `Analyze > Inspect Code` → Android Lint → „HardcodedText“
- schrittweise nach `strings.xml` extrahieren (bessere Pflege + Lokalisierung)

### C) CI/Build Speed
- `org.gradle.configuration-cache=true` kann viel bringen, ist aber plugin-abhängig → optional testen.

---

## 6) Ergebnis
- Projekt-ZIP deutlich schlanker (Build/IDE-Ordner raus)
- minSdk wieder sinnvoll, ohne `java.time` zu verlieren
- Toolchain moderner (Kotlin 2.3 + KSP 2.3.x)
- Manifest sauberer (Kamera-Feature korrekt deklariert)



### Größen-/Datei-Stats
- Dateien vorher: **1831**
- Dateien nachher: **166**
- Größe vorher: **96.9 MB**
- Größe nachher: **0.9 MB**
- Ersparnis: **96.0 MB**
